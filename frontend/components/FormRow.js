import {useFormContext} from "react-hook-form";

export default function FormRow(props) {
    const {register} = useFormContext()
    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td><input id={props.id} readOnly={props.readOnly} type="text" {...register(props.id)}/></td>
    </tr>
}
