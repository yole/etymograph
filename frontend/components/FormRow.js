export default function FormRow(props) {
    const register = props.register
    return <tr>
        <td><label htmlFor={props.id}>{props.label}</label></td>
        <td><input id={props.id} type="text" {...register(props.id)}/></td>
    </tr>
}
