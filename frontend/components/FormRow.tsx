import {useFormContext} from "react-hook-form";
import InputAssist from "@/components/InputAssist";

interface FormRowProps {
    id: string;
    label: string;
    readOnly?: boolean;
    size?: number;
    inputAssist?: boolean;
    children?: React.ReactNode;
    handleBlur?: (data: any) => void;
}

export default function FormRow(props: FormRowProps) {
    const {register, getValues} = useFormContext()

    return <tr onBlur={() => {
        if (props.handleBlur !== undefined) props.handleBlur(getValues())
    }}>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <input id={props.id} readOnly={props.readOnly} size={props.size} type="text" {...register(props.id)}/>
            {props.inputAssist && <InputAssist id={props.id} inline={true}/>}
            {props.children}
        </td>
    </tr>
}
